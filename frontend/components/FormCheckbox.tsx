import {useFormContext} from "react-hook-form";
import {FormFieldProps} from "@/components/FormRow";

export default function FormCheckbox(props: FormFieldProps) {
    const {register} = useFormContext()
    return <>
        <input type="checkbox" {...register(props.id)}/>
        {props.label}
        <br/></>
}
