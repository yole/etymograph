import {FormFieldProps} from "@/components/FormRow";
import {useEtymographFormContext} from "@/components/EtymographForm";

export interface FormCheckboxProps extends FormFieldProps {
    handleChange?: (data: any) => void
}

export default function FormCheckbox(props: FormCheckboxProps) {
    const form = useEtymographFormContext()
    const inputProps = form.getInputProps(props.id, {type: 'checkbox'})
    return <>
        <input type="checkbox"
               {...inputProps}
               onChange={(event) => {
                   inputProps.onChange(event)
                   if (props.handleChange !== undefined) props.handleChange(form.getValues())
               }}/>
        {props.label}
        <br/></>
}
